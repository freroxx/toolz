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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.compose.ui.tooling.preview.Preview
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.R
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
    onNavigateToToolShortcuts: () -> Unit = {},
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
    val caffeinateSummaryNotification by viewModel.caffeinateAutoSummaryNotification.collectAsState(initial = true)
    val purgeShotNotifications by viewModel.purgeShotNotifications.collectAsState(initial = true)

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState(initial = 0xFFFFFFFF.toInt())
    val widgetOpacity by viewModel.widgetOpacity.collectAsState(initial = 0.9f)

    val hapticFeedback by viewModel.hapticFeedback.collectAsState(initial = true)
    val hapticIntensity by viewModel.hapticIntensity.collectAsState(initial = 0.5f)
    val showTopAppBarDescriptions by viewModel.showTopAppBarDescriptions.collectAsState(initial = false)
    val stepCounterEnabled by viewModel.stepCounterEnabled.collectAsState(initial = true)
    val showToolzPill by viewModel.showToolzPill.collectAsState(initial = true)
    val fillThePillEnabled by viewModel.fillThePillEnabled.collectAsState(initial = true)
    val pillTodoEnabled by viewModel.pillTodoEnabled.collectAsState(initial = true)
    val pillFocusEnabled by viewModel.pillFocusEnabled.collectAsState(initial = true)
    val pillMusicEnabled by viewModel.pillMusicEnabled.collectAsState(initial = true)
    val pillTimerEnabled by viewModel.pillTimerEnabled.collectAsState(initial = true)
    val pillStopwatchEnabled by viewModel.pillStopwatchEnabled.collectAsState(initial = true)
    val pillPomodoroEnabled by viewModel.pillPomodoroEnabled.collectAsState(initial = true)
    val pillStepsEnabled by viewModel.pillStepsEnabled.collectAsState(initial = true)
    val pillRecorderEnabled by viewModel.pillRecorderEnabled.collectAsState(initial = true)
    val pillCaffeinateEnabled by viewModel.pillCaffeinateEnabled.collectAsState(initial = true)
    val pillFlashlightEnabled by viewModel.pillFlashlightEnabled.collectAsState(initial = true)
    val pillCatalogDownloadEnabled by viewModel.pillCatalogDownloadEnabled.collectAsState(initial = true)

    val showDashboardStats by viewModel.showDashboardStats.collectAsState(initial = false)
    val appLanguage by viewModel.appLanguage.collectAsState(initial = "en")

    var showPillTweaksPopup by remember { mutableStateOf(false) }

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
            title = { Text(stringResource(R.string.st_SettingsScreen_a1b2), fontWeight = FontWeight.Black) },
            text = { Text(stringResource(R.string.st_SettingsScreen_c3d4)) },
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
                    Text(stringResource(R.string.st_SettingsScreen_e5f6), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrationManager?.vibrateClick()
                    showGraySuggestion = false
                }) {
                    @Suppress("DEPRECATION")
                    Text(stringResource(R.string.st_SettingsScreen_g7h8), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState(initial = false)
    val aiSearchEnabled by viewModel.aiSearchEnabled.collectAsState(initial = false)
    val aiSearchChatEnabled by viewModel.aiSearchChatEnabled.collectAsState(initial = true)
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState(initial = false)

    val musicShakeToSkip by viewModel.musicShakeToSkip.collectAsState(initial = false)
    val musicShakeSensitivity by viewModel.musicShakeSensitivity.collectAsState(initial = 0.3f)
    val musicAudioFocus by viewModel.musicAudioFocus.collectAsState(initial = true)
    val musicAudioFocusDucking by viewModel.musicAudioFocusDucking.collectAsState(initial = false)
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

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}
            viewModel.setCustomRingtoneUri(it.toString())
        }
    }


    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.st_SettingsScreen_i9j0), fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text(stringResource(R.string.st_SettingsScreen_k1l2)) },
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
                    Text(stringResource(R.string.st_SettingsScreen_m3n4), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrationManager?.vibrateClick()
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.st_SettingsScreen_o5p6), fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showPillTweaksPopup) {
        PillTweaksPopup(
            viewModel = viewModel,
            onDismiss = { showPillTweaksPopup = false }
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
                title = stringResource(R.string.st_SettingsScreen_q7r8),
                subtitle = stringResource(R.string.st_SettingsScreen_s9t0),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_SettingsScreen_u1v2))
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
                        Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.st_SettingsScreen_w3x4), tint = MaterialTheme.colorScheme.error)
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
                            title = stringResource(R.string.st_SettingsScreen_c9d0),
                            icon = Icons.Rounded.AccountCircle,
                            isExpanded = expandedSection == "ACCOUNT" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "ACCOUNT") null else "ACCOUNT" }
                        ) {
                            if (matches(searchQuery, "profile", "name", "explorer", "identity")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_SettingsScreen_e1f2),
                                    subtitle = "Name: ${userName.ifBlank { "Explorer" }}",
                                    icon = Icons.Rounded.Badge
                                ) {
                                    OutlinedTextField(
                                        value = userNameInput,
                                        onValueChange = { userNameInput = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        placeholder = { Text(stringResource(R.string.st_SettingsScreen_g3h4)) },
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
                            title = stringResource(R.string.st_SettingsScreen_i5j6),
                            icon = Icons.Rounded.Palette,
                            isExpanded = expandedSection == "VISUALS" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "VISUALS") null else "VISUALS" }
                        ) {
                            if (matches(searchQuery, "performance", "lag", "animations", "blur", "speed", "optimization")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_k7l8),
                                    subtitle = stringResource(R.string.st_SettingsScreen_m9n0),
                                    icon = Icons.Rounded.Speed,
                                    checked = performanceMode,
                                    onCheckedChange = { viewModel.setPerformanceMode(it) }
                                )
                            }

                            if (matches(searchQuery, "dark", "light", "mode", "appearance", "theme")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_SettingsScreen_o1p2),
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
                                    title = stringResource(R.string.st_SettingsScreen_q3r4),
                                    subtitle = stringResource(R.string.st_SettingsScreen_s5t6),
                                    icon = Icons.Rounded.ColorLens,
                                    checked = dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )

                                if (!dynamicColor) {
                                    SettingsItem(
                                        title = stringResource(R.string.st_SettingsScreen_u7v8),
                                        subtitle = stringResource(R.string.st_SettingsScreen_w9x0),
                                        icon = Icons.Rounded.FormatColorFill
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                    @Suppress("DEPRECATION")
                                                    Text(stringResource(R.string.st_SettingsScreen_y1z2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
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
                                                    Text(stringResource(R.string.st_SettingsScreen_a3b5), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.5.sp)
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
                                                Text(stringResource(R.string.st_SettingsScreen_c5d7), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "gradient", "background", "effect", "visual")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_e7f9),
                                    subtitle = stringResource(R.string.st_SettingsScreen_g9h1),
                                    icon = Icons.Rounded.Gradient,
                                    checked = backgroundGradientEnabled,
                                    onCheckedChange = { viewModel.setBackgroundGradientEnabled(it) }
                                )
                            }

                            if (matches(searchQuery, "recent", "home", "layout", "view", "dashboard", "style")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_SettingsScreen_i1j3),
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

                            // Removed Widget Styling section as requested
                        }
                    }

                    // 3. Section: INTELLIGENCE & AI
                    StaggeredEntrance(index = 2) {
                        SettingsExpandableSection(
                            title = stringResource(R.string.st_SettingsScreen_k3l5),
                            icon = Icons.Rounded.AutoAwesome,
                            isExpanded = expandedSection == "INTELLIGENCE" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "INTELLIGENCE") null else "INTELLIGENCE" }
                        ) {
                            if (matches(searchQuery, "offline", "local", "internet", "network", "privacy")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_m5n7),
                                    subtitle = stringResource(R.string.st_SettingsScreen_o7p9),
                                    icon = Icons.Rounded.CloudOff,
                                    checked = offlineModeEnabled,
                                    onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
                                )
                            }
                            if (!offlineModeEnabled) {
                                if (matches(searchQuery, "ai", "search", "conversational", "smart", "chat")) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_q9r1),
                                        subtitle = stringResource(R.string.st_SettingsScreen_s1t3),
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = aiSearchChatEnabled,
                                        onCheckedChange = { viewModel.setAiSearchChatEnabled(it) }
                                    )
                                }
                                if (matches(searchQuery, "ai", "clipboard", "monitoring", "summarize", "smart")) {
                                    val aiMonitoring by viewModel.aiClipboardMonitoringEnabled.collectAsState(initial = true)
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_u3v5),
                                        subtitle = stringResource(R.string.st_SettingsScreen_w5x7),
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = aiMonitoring,
                                        onCheckedChange = { viewModel.setAiClipboardMonitoringEnabled(it) },
                                        enabled = !offlineModeEnabled
                                    )
                                }
                                if (matches(searchQuery, "pdf", "ocr", "enhance", "ai", "text", "scan")) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_y7z9),
                                        subtitle = stringResource(R.string.st_SettingsScreen_a9b1),
                                        icon = Icons.Rounded.DocumentScanner,
                                        checked = pdfAiOcrEnhance,
                                        onCheckedChange = { viewModel.setPdfAiOcrEnhance(it) },
                                        enabled = !offlineModeEnabled
                                    )
                                }
                                if (matches(searchQuery, "now playing", "lyrics", "meaning", "smart", "ai")) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_c1d3),
                                        subtitle = stringResource(R.string.st_SettingsScreen_e3f5),
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
                            title = stringResource(R.string.st_SettingsScreen_g5h7),
                            icon = Icons.Rounded.SmartButton,
                            isExpanded = expandedSection == "INTERACTION" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "INTERACTION") null else "INTERACTION" }
                        ) {
                            if (matches(searchQuery, "pill", "smart", "overlay", "todo", "focus", "fill", "hud")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_i7j9),
                                    subtitle = stringResource(R.string.st_SettingsScreen_k9l1),
                                    icon = Icons.Rounded.SmartButton,
                                    checked = showToolzPill,
                                    onCheckedChange = { viewModel.setShowToolzPill(it) }
                                )

                                if (showToolzPill) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_m1n3),
                                        subtitle = stringResource(R.string.st_SettingsScreen_o3p5),
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = fillThePillEnabled,
                                        onCheckedChange = { viewModel.setFillThePillEnabled(it) }
                                    )

                                    SettingsItem(
                                        title = stringResource(R.string.st_SettingsScreen_q5r7),
                                        subtitle = stringResource(R.string.st_SettingsScreen_s7t9),
                                        icon = Icons.Rounded.Tune,
                                        onClick = {
                                            vibrationManager?.vibrateClick()
                                            showPillTweaksPopup = true
                                        }
                                    )
                                }
                            }

                            if (matches(searchQuery, "dashboard", "stats", "battery", "storage", "info")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_u9v1),
                                    subtitle = stringResource(R.string.st_SettingsScreen_w1x3),
                                    icon = Icons.Rounded.BarChart,
                                    checked = showDashboardStats,
                                    onCheckedChange = { viewModel.setShowDashboardStats(it) }
                                )
                            }

                            if (matches(searchQuery, "vibration", "haptic", "intensity", "feedback", "tuner")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_y3z5),
                                    subtitle = stringResource(R.string.st_SettingsScreen_a5b7),
                                    icon = Icons.Rounded.Vibration,
                                    checked = hapticFeedback,
                                    onCheckedChange = { viewModel.setHapticFeedback(it) }
                                )

                                if (hapticFeedback) {
                                    SettingsItem(
                                        title = stringResource(R.string.st_SettingsScreen_c7d9),
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

                            if (matches(searchQuery, "top bar", "app bar", "description", "subtitle", "expressive", "hud")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_top_bar_desc_title),
                                    subtitle = stringResource(R.string.st_SettingsScreen_top_bar_desc_subtitle),
                                    icon = Icons.Rounded.Subtitles,
                                    checked = showTopAppBarDescriptions,
                                    onCheckedChange = { viewModel.setShowTopAppBarDescriptions(it) }
                                )
                            }
                        }
                    }

                    // 5. Section: MEDIA & AUDIO
                    StaggeredEntrance(index = 4) {
                        SettingsExpandableSection(
                            title = stringResource(R.string.st_SettingsScreen_m7n9),
                            icon = Icons.Rounded.MusicNote,
                            isExpanded = expandedSection == "MEDIA" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "MEDIA") null else "MEDIA" }
                        ) {
                            if (matches(searchQuery, "shake", "skip", "audio", "sensitivity", "gesture")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_o9p1),
                                    subtitle = stringResource(R.string.st_SettingsScreen_q1r3),
                                    icon = Icons.Rounded.PhonelinkRing,
                                    checked = musicShakeToSkip,
                                    onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                                )
                                
                                if (musicShakeToSkip) {
                                    SettingsItem(
                                        title = stringResource(R.string.st_SettingsScreen_s3t5),
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
                                    title = stringResource(R.string.st_SettingsScreen_u5v7),
                                    subtitle = stringResource(R.string.st_SettingsScreen_w7x9),
                                    icon = Icons.Rounded.Visibility,
                                    checked = musicKeepScreenOnLyrics,
                                    onCheckedChange = { viewModel.setMusicKeepScreenOnLyrics(it) }
                                )
                            }

                            if (matches(searchQuery, "audio", "focus", "pause", "smart", "ducking", "allow", "duck", "volume", "lower")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_y9z1),
                                    subtitle = stringResource(R.string.st_SettingsScreen_a1b4),
                                    icon = Icons.Rounded.Hearing,
                                    checked = musicAudioFocus,
                                    onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                                )
                                AnimatedVisibility(
                                    visible = musicAudioFocus,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.st_SettingsScreen_c3d6),
                                        subtitle = stringResource(R.string.st_SettingsScreen_e5f8),
                                        icon = Icons.AutoMirrored.Rounded.VolumeDown,
                                        checked = musicAudioFocusDucking,
                                        onCheckedChange = { viewModel.setMusicAudioFocusDucking(it) }
                                    )
                                }
                            }

                            if (matches(searchQuery, "karaoke", "mic", "sing", "audio")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_g7h1),
                                    subtitle = stringResource(R.string.st_SettingsScreen_i9j2),
                                    icon = Icons.Rounded.Mic,
                                    checked = karaokeEnabled,
                                    onCheckedChange = { viewModel.setKaraokeEnabled(it) }
                                )
                            }

                            if (matches(searchQuery, "ringtone", "sound", "custom", "audio", "timer", "pomodoro")) {
                                val customRingtoneEnabled by viewModel.customRingtoneEnabled.collectAsState(initial = false)
                                val customRingtoneUri by viewModel.customRingtoneUri.collectAsState(initial = null)
                                
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_k1l3),
                                    subtitle = stringResource(R.string.st_SettingsScreen_m3n5),
                                    icon = Icons.Rounded.MusicNote,
                                    checked = customRingtoneEnabled,
                                    onCheckedChange = { viewModel.setCustomRingtoneEnabled(it) }
                                )

                                AnimatedVisibility(
                                    visible = customRingtoneEnabled,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        ToolzExpressiveButton(
                                            onClick = { 
                                                vibrationManager?.vibrateClick()
                                                ringtoneLauncher.launch(arrayOf("audio/*"))
                                            },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = ExtraLargeExpressiveShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                if (customRingtoneUri != null) stringResource(R.string.st_SettingsScreen_o5p7) else stringResource(R.string.st_SettingsScreen_q7r9),
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        
                                        if (customRingtoneUri != null) {
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text(
                                                    stringResource(R.string.st_SettingsScreen_s9t1),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.weight(1f))
                                                TextButton(onClick = { viewModel.setCustomRingtoneUri(null) }) {
                                                    Text(stringResource(R.string.st_SettingsScreen_u1v3), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }

                    // 6. Section: HEALTH & ACTIVITY
                    StaggeredEntrance(index = 5) {
                        SettingsExpandableSection(
                            title = stringResource(R.string.st_SettingsScreen_w3x5),
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            isExpanded = expandedSection == "HEALTH" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "HEALTH") null else "HEALTH" }
                        ) {
                            if (matches(searchQuery, "step", "goal", "health", "tracker", "walking")) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.st_SettingsScreen_y5z7),
                                    subtitle = stringResource(R.string.st_SettingsScreen_a7b9),
                                    icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                    checked = stepCounterEnabled,
                                    onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                                )

                                if (stepCounterEnabled) {
                                    SettingsItem(
                                        title = stringResource(R.string.st_SettingsScreen_c9d1),
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
                            title = stringResource(R.string.st_SettingsScreen_e1f3),
                            icon = Icons.Rounded.Notifications,
                            isExpanded = expandedSection == "NOTIFICATIONS" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "NOTIFICATIONS") null else "NOTIFICATIONS" }
                        ) {
                            if (matches(searchQuery, "notification", "master", "switch", "alerts")) {
                                SettingsToggleItem(
                    title = stringResource(R.string.st_SettingsScreen_g3h5),
                    subtitle = stringResource(R.string.st_SettingsScreen_i5j7),
                    icon = Icons.Rounded.NotificationsActive,
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
                
                SettingsToggleItem(
                    title = stringResource(R.string.st_SettingsScreen_y5z7),
                    subtitle = stringResource(R.string.st_SettingsScreen_k7l9),
                    icon = Icons.Rounded.DirectionsRun,
                    checked = stepNotifications,
                    onCheckedChange = { viewModel.setStepNotifications(it) }
                )

                                if (notificationsEnabled) {
                                    if (matches(searchQuery, "vault", "history", "save")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_m9n1),
                                            subtitle = stringResource(R.string.st_SettingsScreen_o1p3),
                                            icon = Icons.Rounded.History,
                                            checked = notificationVaultEnabled,
                                            onCheckedChange = { viewModel.setNotificationVaultEnabled(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "step", "goal", "alert")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_q3r5),
                                            subtitle = stringResource(R.string.st_SettingsScreen_s5t7),
                                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                            checked = stepNotifications,
                                            onCheckedChange = { viewModel.setStepNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "timer", "alert", "task")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_u7v9),
                                            subtitle = stringResource(R.string.st_SettingsScreen_w9x1),
                                            icon = Icons.Rounded.Timer,
                                            checked = timerNotifications,
                                            onCheckedChange = { viewModel.setTimerNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "music", "playback", "media")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_y1z3),
                                            subtitle = stringResource(R.string.st_SettingsScreen_a3b6),
                                            icon = Icons.Rounded.MusicNote,
                                            checked = musicNotifications,
                                            onCheckedChange = { viewModel.setMusicNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "conversion", "file", "progress")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_c5d8),
                                            subtitle = stringResource(R.string.st_SettingsScreen_e7f1),
                                            icon = Icons.Rounded.Transform,
                                            checked = fileConversionNotifications,
                                            onCheckedChange = { viewModel.setFileConversionNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "task", "reminder", "deadline")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_g9h2),
                                            subtitle = stringResource(R.string.st_SettingsScreen_i1j4),
                                            icon = Icons.Rounded.TaskAlt,
                                            checked = taskReminderNotifications,
                                            onCheckedChange = { viewModel.setTaskReminderNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "event", "calendar", "reminder")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_k3l6),
                                            subtitle = stringResource(R.string.st_SettingsScreen_m5n8),
                                            icon = Icons.Rounded.Event,
                                            checked = eventReminderNotifications,
                                            onCheckedChange = { viewModel.setEventReminderNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "pomodoro", "focus", "timer")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_o7p1),
                                            subtitle = stringResource(R.string.st_SettingsScreen_q9r2),
                                            icon = Icons.Rounded.AvTimer,
                                            checked = pomodoroNotifications,
                                            onCheckedChange = { viewModel.setPomodoroNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "flashlight", "light", "notification")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_s1t4),
                                            subtitle = stringResource(R.string.st_SettingsScreen_u3v6),
                                            icon = Icons.Rounded.FlashlightOn,
                                            checked = flashlightNotificationsEnabled,
                                            onCheckedChange = { viewModel.setFlashlightNotificationsEnabled(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "update", "app", "version")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_w5x8),
                                            subtitle = stringResource(R.string.st_SettingsScreen_y7z1),
                                            icon = Icons.Rounded.SystemUpdate,
                                            checked = appUpdateNotifications,
                                            onCheckedChange = { viewModel.setAppUpdateNotifications(it) }
                                        )
                                    }

                                    if (matches(searchQuery, "caffeinate", "auto", "summary", "awake")) {
                                        SettingsToggleItem(
                                            title = stringResource(R.string.st_SettingsScreen_a9b2),
                                            subtitle = stringResource(R.string.st_SettingsScreen_c1d4),
                                            icon = Icons.Rounded.Coffee,
                                            checked = caffeinateSummaryNotification,
                                            onCheckedChange = { viewModel.setCaffeinateAutoSummaryNotification(it) }
                                        )
                                    }

                                    if (matches(searchQuery, "purgeshot", "screenshot", "delete", "scheduled")) {
                                        SettingsToggleItem(
                                            title = "PurgeShot",
                                            subtitle = "Show notification when screenshot deletion is scheduled",
                                            icon = Icons.Rounded.ScreenshotMonitor,
                                            checked = purgeShotNotifications,
                                            onCheckedChange = { viewModel.setPurgeShotNotifications(it) }
                                        )
                                    }
                                }
                            }

                        }
                    }

                    // 8. Section: SYSTEM & DATA
                    StaggeredEntrance(index = 7) {
                        SettingsExpandableSection(
                            title = stringResource(R.string.st_Settings_Section_System),
                            icon = Icons.Rounded.SettingsInputComponent,
                            isExpanded = expandedSection == "SYSTEM" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "SYSTEM") null else "SYSTEM" }
                        ) {
                            if (matches(searchQuery, "backup", "restore", "data", "save", "export", "import")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_Backup_Title),
                                    subtitle = stringResource(R.string.st_Backup_Subtitle),
                                    icon = Icons.Rounded.Backup,
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        onNavigateToBackupRestore()
                                    }
                                )
                            }

                            if (matches(searchQuery, "shortcut", "pin", "home screen", "tool shortcut", "launcher")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_Shortcut_Manage_Title),
                                    subtitle = stringResource(R.string.st_Shortcut_Manage_Desc),
                                    icon = Icons.Rounded.AddToHomeScreen,
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        onNavigateToToolShortcuts()
                                    }
                                )
                            }

                            if (matches(searchQuery, "converter", "output", "path", "folder", "save", "storage")) {
                                SettingsItem(
                                    title = stringResource(R.string.st_Settings_OutputFolder),
                                    subtitle = if (converterCustomPath == null) stringResource(R.string.st_Settings_OutputFolder_Default) else stringResource(R.string.st_Settings_OutputFolder_Custom),
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
                                                stringResource(R.string.st_Settings_OutputFolder_CustomPath),
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
                                    title = stringResource(R.string.st_Settings_ResetDeviceInfo),
                                    subtitle = stringResource(R.string.st_Settings_ResetDeviceInfo_Desc),
                                    icon = Icons.Rounded.RestartAlt,
                                    onClick = {
                                        vibrationManager?.vibrateSuccess()
                                        viewModel.clearDeviceInfoCache()
                                        android.widget.Toast.makeText(context, context.getString(R.string.st_Settings_DeviceInfo_Cleared), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // 9. Section: LANGUAGES
                    StaggeredEntrance(index = 8) {
                        SettingsExpandableSection(
                            title = "LANGUAGES",
                            icon = Icons.Rounded.Language,
                            isExpanded = expandedSection == "LANGUAGES" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "LANGUAGES") null else "LANGUAGES" }
                        ) {
                            val languages = listOf(
                                Triple("English", "en", "🇺🇸"),
                                Triple("Português (Brasil)", "pt-BR", "🇧🇷"),
                                Triple("Español", "es", "🇪🇸"),
                                Triple("Français", "fr", "🇫🇷")
                            )

                            languages.forEach { (name, code, flag) ->
                                if (matches(searchQuery, name, code)) {
                                    val isSelected = appLanguage == code
                                    SettingsItem(
                                        title = name,
                                        subtitle = if (isSelected) "Active" else "Switch to $name",
                                        icon = Icons.Rounded.Language
                                    ) {
                                        Surface(
                                            onClick = {
                                                vibrationManager?.vibrateSuccess()
                                                viewModel.setAppLanguage(code)
                                                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(code)
                                                AppCompatDelegate.setApplicationLocales(appLocale)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp).bouncyClick {},
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(flag, fontSize = 20.sp)
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(
                                                        name,
                                                        fontWeight = FontWeight.Black,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }

                    // 10. Section: UPDATE
                    if (!offlineModeEnabled) {
                        StaggeredEntrance(index = 9) {
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

                    // 11. Section: ABOUT
                    StaggeredEntrance(index = 10) {
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
                stringResource(R.string.st_SettingsScreen_y5z6),
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
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_SettingsScreen_a7b8), modifier = Modifier.size(20.dp))
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
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.st_About_Toolz), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            @Suppress("DEPRECATION")
            Text("V${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.st_About_Credits),
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
                    Text(stringResource(R.string.st_About_Updates), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
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
                    Text(stringResource(R.string.st_About_Discord), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            @Suppress("DEPRECATION")
            Text(
                stringResource(R.string.st_Haptic_Intensity),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                "${(intensity * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ExpressiveSlider(
            value = intensity,
            onValueChange = { 
                onIntensityChange(it)
            },
            onValueChangeFinished = { haptic.click() },
            valueRange = 0.1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.st_Haptic_Soft to 0.1f,
                R.string.st_Haptic_Crisp to 0.5f,
                R.string.st_Haptic_Strong to 1.0f
            ).forEach { (labelRes, target) ->
                val isSelected = (intensity - target).let { if (it < 0) -it else it } < 0.2f
                
                Surface(
                    onClick = { 
                        onIntensityChange(target)
                        haptic.click()
                    },
                    modifier = Modifier.weight(1f).height(44.dp).bouncyClick {},
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        @Suppress("DEPRECATION")
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        shape = RoundedCornerShape(20.dp),
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    @Suppress("DEPRECATION")
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    @Suppress("DEPRECATION")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
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
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
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

@Composable
fun PillTweaksPopup(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current

    val pillMusicEnabled by viewModel.pillMusicEnabled.collectAsState(initial = true)
    val pillTimerEnabled by viewModel.pillTimerEnabled.collectAsState(initial = true)
    val pillStopwatchEnabled by viewModel.pillStopwatchEnabled.collectAsState(initial = true)
    val pillPomodoroEnabled by viewModel.pillPomodoroEnabled.collectAsState(initial = true)
    val pillStepsEnabled by viewModel.pillStepsEnabled.collectAsState(initial = true)
    val pillRecorderEnabled by viewModel.pillRecorderEnabled.collectAsState(initial = true)
    val pillTodoEnabled by viewModel.pillTodoEnabled.collectAsState(initial = true)
    val pillCaffeinateEnabled by viewModel.pillCaffeinateEnabled.collectAsState(initial = true)
    val pillFlashlightEnabled by viewModel.pillFlashlightEnabled.collectAsState(initial = true)
    val pillFocusEnabled by viewModel.pillFocusEnabled.collectAsState(initial = true)
    val pillCatalogDownloadEnabled by viewModel.pillCatalogDownloadEnabled.collectAsState(initial = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.st_PillTweaks_Title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        @Suppress("DEPRECATION")
                        Text(
                            stringResource(R.string.st_PillTweaks_Subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onDismiss()
                        },
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_MusicPlayer),
                        subtitle = stringResource(R.string.st_PillTweaks_Music_Desc),
                        icon = Icons.Rounded.MusicNote,
                        checked = pillMusicEnabled,
                        onCheckedChange = { viewModel.setPillMusicEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_Timer),
                        subtitle = stringResource(R.string.st_PillTweaks_Timer_Desc),
                        icon = Icons.Rounded.Timer,
                        checked = pillTimerEnabled,
                        onCheckedChange = { viewModel.setPillTimerEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_Stopwatch),
                        subtitle = stringResource(R.string.st_PillTweaks_Stopwatch_Desc),
                        icon = Icons.Rounded.AvTimer,
                        checked = pillStopwatchEnabled,
                        onCheckedChange = { viewModel.setPillStopwatchEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_Pomodoro),
                        subtitle = stringResource(R.string.st_PillTweaks_Pomodoro_Desc),
                        icon = Icons.Rounded.AvTimer,
                        checked = pillPomodoroEnabled,
                        onCheckedChange = { viewModel.setPillPomodoroEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_StepCounter),
                        subtitle = stringResource(R.string.st_PillTweaks_Steps_Desc),
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        checked = pillStepsEnabled,
                        onCheckedChange = { viewModel.setPillStepsEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_VoiceRecorder),
                        subtitle = stringResource(R.string.st_PillTweaks_Recorder_Desc),
                        icon = Icons.Rounded.Mic,
                        checked = pillRecorderEnabled,
                        onCheckedChange = { viewModel.setPillRecorderEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_PillTweaks_Todo),
                        subtitle = stringResource(R.string.st_PillTweaks_Todo_Desc),
                        icon = Icons.Rounded.TaskAlt,
                        checked = pillTodoEnabled,
                        onCheckedChange = { viewModel.setPillTodoEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_PillTweaks_Focus),
                        subtitle = stringResource(R.string.st_PillTweaks_Focus_Desc),
                        icon = Icons.Rounded.Toll,
                        checked = pillFocusEnabled,
                        onCheckedChange = { viewModel.setPillFocusEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_Caffeinate),
                        subtitle = stringResource(R.string.st_PillTweaks_Caffeinate_Desc),
                        icon = Icons.Rounded.Coffee,
                        checked = pillCaffeinateEnabled,
                        onCheckedChange = { viewModel.setPillCaffeinateEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_Tool_Flashlight),
                        subtitle = stringResource(R.string.st_PillTweaks_Flashlight_Desc),
                        icon = Icons.Rounded.FlashlightOn,
                        checked = pillFlashlightEnabled,
                        onCheckedChange = { viewModel.setPillFlashlightEnabled(it) }
                    )
                    SettingsToggleItem(
                        title = stringResource(R.string.st_PillTweaks_Downloads),
                        subtitle = stringResource(R.string.st_PillTweaks_Downloads_Desc),
                        icon = Icons.Rounded.Download,
                        checked = pillCatalogDownloadEnabled,
                        onCheckedChange = { viewModel.setPillCatalogDownloadEnabled(it) }
                    )
                }

                Spacer(Modifier.height(24.dp))

                ToolzExpressiveButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = ExtraLargeExpressiveShape
                ) {
                    @Suppress("DEPRECATION")
                    Text(stringResource(R.string.st_PillTweaks_Done), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
