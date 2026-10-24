/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.PinResult
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzMark
import com.frerox.toolz.ui.components.ToolzMarkThemed
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * Dedicated App Skin screen: icon presets + appearance + community skins.
 * Reached from Settings → App Skin. Full-screen, not a cramped expandable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSkinScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSkins: () -> Unit = {},
) {
    val vibrationManager = LocalVibrationManager.current
    val scope = rememberCoroutineScope()

    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
    val presetId by viewModel.appIconPresetId.collectAsState(initial = "grape-haze")
    val shape by viewModel.appIconShape.collectAsState(initial = "HEXAGON")
    val adaptTheme by viewModel.appIconAdaptTheme.collectAsState(initial = true)
    val followDynamic by viewModel.appIconFollowDynamic.collectAsState(initial = true)
    val bgOv by viewModel.appIconBgOverride.collectAsState(initial = null)
    val fgOv by viewModel.appIconFgOverride.collectAsState(initial = null)
    val strokeOv by viewModel.appIconStrokeOverride.collectAsState(initial = null)
    val iconGradient by viewModel.appIconGradientEnabled.collectAsState(initial = true)
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState(initial = null)
    val activeSkinId by viewModel.activeSkinId.collectAsState(initial = null)
    val recentIds by viewModel.recentSkinIds.collectAsState(initial = emptyList())
    val skins by viewModel.skins.collectAsState(initial = emptyList())
    val syncing by viewModel.skinsSyncing.collectAsState(initial = false)
    val userName by viewModel.userName.collectAsState(initial = "")
    val mySubmissions by viewModel.mySubmissions.collectAsState(initial = emptyList())
    val submissionStatuses by viewModel.submissionStatuses.collectAsState(initial = emptyMap())

    var showShare by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<Result<String>?>(null) }
    var showAdvancedThemeDialog by remember { mutableStateOf(false) }
    var showGraySuggestion by remember { mutableStateOf(false) }

    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = MaterialTheme.colorScheme
    val current = IconCatalog.find(presetId)

    // Home-screen pins: a launcher icon pack overrides the alias tile, so the
    // only way to show the chosen art is a shortcut carrying the real bitmap.
    val pinContext = LocalContext.current
    val pinAddedText = stringResource(R.string.st_AppSkin_pin_added)
    val pinUpdatedText = stringResource(R.string.st_AppSkin_pin_updated)
    val pinUnsupportedText = stringResource(R.string.st_AppSkin_pin_unsupported)
    val pinFailedText = stringResource(R.string.st_AppSkin_pin_failed)
    var pinMessage by remember { mutableStateOf<String?>(null) }
    val showPinResult: (PinResult) -> Unit = { result ->
        pinMessage = when (result) {
            PinResult.Pinned -> pinAddedText
            PinResult.Updated -> pinUpdatedText
            PinResult.Unsupported -> pinUnsupportedText
            PinResult.Failed -> pinFailedText
        }
    }
    LaunchedEffect(pinMessage) {
        val message = pinMessage ?: return@LaunchedEffect
        Toast.makeText(pinContext, message, Toast.LENGTH_SHORT).show()
        pinMessage = null
    }

    // Recently used skins first (max 3); fresh installs fall back to the bundled looks.
    val recentSkins = remember(skins, recentIds) {
        val byId = skins.associateBy { it.id }
        recentIds.mapNotNull { byId[it] }.take(3).ifEmpty { skins.take(3) }
    }

    LaunchedEffect(Unit) { viewModel.refreshSkins(false) }

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
            shape = RoundedCornerShape(28.dp),
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
                title = stringResource(R.string.st_AppSkin_screen_title),
                subtitle = stringResource(R.string.st_AppSkin_screen_subtitle),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_SettingsScreen_u1v2))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 16.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // Hero preview
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        ToolzMarkThemed(
                            presetId = presetId,
                            isDark = isDark,
                            dynamicColor = dynamicColor,
                            scheme = scheme,
                            shape = shape,
                            adaptToTheme = adaptTheme,
                            followDynamic = followDynamic,
                            bgOverride = bgOv,
                            fgOverride = fgOv,
                            strokeOverride = strokeOv,
                            iconGradient = iconGradient,
                            modifier = Modifier.size(112.dp).bouncyClick(
                                onLongClick = {
                                    // "custom-photo" has no catalog art; pin the file instead.
                                    if (presetId == IconCatalog.CUSTOM_ID) {
                                        viewModel.pinCustomPhotoToHome(showPinResult)
                                    } else {
                                        viewModel.pinPresetToHome(presetId, showPinResult)
                                    }
                                },
                            ),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(
                                "${iconShapeLabel(shape).lowercase()} · ${iconTagLabel(current.tag).lowercase()} · ${activeSkinId ?: stringResource(R.string.st_AppSkin_custom_look)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            ToolzExpressiveButton(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    onOpenCatalog()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                Icon(Icons.Rounded.GridView, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_AppSkin_catalog_title), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                AppSkinSectionHeader(stringResource(R.string.st_AppSkin_header_app_icon))

                IconPackInfoCard()

                // Icon presets strip
                SettingsItem(
                    title = stringResource(R.string.st_AppSkin_icon_style),
                    subtitle = stringResource(R.string.st_AppSkin_presets_hint, IconCatalog.BUILT_IN.size),
                    icon = Icons.Rounded.GridView,
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).horizontalFadingEdges(left = 12.dp, right = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(IconCatalog.BUILT_IN, key = { it.id }) { spec ->
                            val selected = spec.id == presetId
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    ),
                                    modifier = Modifier.size(68.dp).bouncyClick(
                                        onClick = { viewModel.setAppIconPresetId(spec.id) },
                                        onLongClick = { viewModel.pinPresetToHome(spec.id, showPinResult) },
                                    ),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(9.dp)) {
                                        ToolzMarkThemed(
                                            presetId = spec.id,
                                            isDark = isDark,
                                            dynamicColor = dynamicColor,
                                            scheme = scheme,
                                            shape = shape,
                                            adaptToTheme = adaptTheme,
                                            followDynamic = followDynamic,
                                            iconGradient = iconGradient,
                                            modifier = Modifier.size(50.dp),
                                        )
                                    }
                                }
                                Text(
                                    spec.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                                    maxLines = 1,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Photo icon — developer-mode experiment. It stays behind the
                // dev toggle because a device photo cannot become the launcher
                // tile (Android needs the art to be predeclared in the manifest).
                val developerModeEnabled by viewModel.developerModeEnabled.collectAsState(initial = false)
                if (developerModeEnabled) {
                    val customPath by viewModel.appIconCustomPath.collectAsState(initial = null)
                    var customBusy by remember { mutableStateOf(false) }
                    var customFailed by remember { mutableStateOf(false) }
                    // ToolzMark caches the decoded file by preset id; the custom
                    // file keeps the same path on replace, so bump this key to
                    // force a fresh decode in the preview.
                    var customVersion by remember { mutableStateOf(0) }
                    val photoPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            customBusy = true
                            customFailed = false
                            viewModel.applyCustomPhoto(uri) { ok ->
                                customBusy = false
                                customFailed = !ok
                                if (ok) customVersion++
                            }
                        }
                    }
                    PhotoIconCard(
                        hasPhoto = customPath != null,
                        isActive = presetId == IconCatalog.CUSTOM_ID,
                        shape = shape,
                        photoVersion = customVersion,
                        busy = customBusy,
                        failed = customFailed,
                        onPick = {
                            customFailed = false
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onUseSaved = {
                            customFailed = false
                            viewModel.setAppIconPresetId(IconCatalog.CUSTOM_ID)
                        },
                        onRemove = {
                            customFailed = false
                            viewModel.clearCustomPhoto()
                        },
                        onPin = { viewModel.pinCustomPhotoToHome(showPinResult) },
                    )
                }

                // Icon container — in-app frames. Circle also reshapes the
                // home-screen tile to the round variant of the preset.
                SettingsItem(
                    title = stringResource(R.string.st_AppSkin_shape_title),
                    subtitle = stringResource(R.string.st_AppSkin_shape_sub),
                    icon = Icons.Rounded.Crop,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "OCTAGON" to stringResource(R.string.st_AppSkin_shape_octagon),
                            "CIRCLE" to stringResource(R.string.st_AppSkin_shape_circle),
                        ).forEach { (s, label) ->
                            val sel = com.frerox.toolz.data.skin.IconShape.parse(shape) == com.frerox.toolz.data.skin.IconShape.parse(s)
                            Surface(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.setAppIconShape(s)
                                },
                                modifier = Modifier.weight(1f).height(46.dp).bouncyClick {},
                                shape = RoundedCornerShape(14.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                AppSkinSectionHeader(stringResource(R.string.st_AppSkin_colors_title))

                // Merged color behavior: dynamic palette + custom colors + adapt + follow + gradient.
                // Adapt works on every icon; follow stays exclusive to scheme-driven icons.
                SettingsItem(
                    title = stringResource(R.string.st_AppSkin_colors_title),
                    subtitle = stringResource(R.string.st_AppSkin_colors_sub, current.name),
                    icon = Icons.Rounded.ColorLens,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SkinMiniSwitch(
                            title = stringResource(R.string.st_AppSkin_dynamic),
                            desc = stringResource(
                                if (dynamicColor) R.string.st_AppSkin_dynamic_on
                                else R.string.st_AppSkin_dynamic_off
                            ),
                            checked = dynamicColor,
                            onCheckedChange = {
                                vibrationManager?.vibrateClick()
                                viewModel.setDynamicColor(it)
                            },
                        )
                        if (!dynamicColor) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 10.dp)) {
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
                                            if (isDark && Color(it).appSkinLuminance() < 0.2f) {
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
                                androidx.compose.material3.Button(
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
                                    @Suppress("DEPRECATION")
                                    Text(stringResource(R.string.st_SettingsScreen_c5d7), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        // Adapt works on every preset now: the icon dims its body in dark
                        // mode, and (with Dynamic colors off) the app accents follow the
                        // icon's tuned light/dark theme unless custom colors override it.
                        SkinMiniSwitch(
                            title = stringResource(R.string.st_AppSkin_adapt),
                            desc = stringResource(
                                if (dynamicColor) R.string.st_AppSkin_adapt_dyn
                                else R.string.st_AppSkin_adapt_full,
                                current.name,
                            ),
                            checked = adaptTheme,
                            onCheckedChange = {
                                vibrationManager?.vibrateClick()
                                viewModel.setAppIconAdaptTheme(it)
                            },
                        )
                        if (!dynamicColor && adaptTheme) {
                            val modeWord = themeModeLabel(themeMode)
                            SkinMiniNote(
                                title = stringResource(R.string.st_AppSkin_accent_source),
                                desc = if (customPrimaryInt == null && customSecondaryInt == null)
                                    stringResource(R.string.st_AppSkin_accent_icon, current.name, modeWord)
                                else stringResource(R.string.st_AppSkin_accent_custom, current.name),
                            )
                        }
                        if (current.followsScheme) {
                            SkinMiniSwitch(
                                title = stringResource(R.string.st_AppSkin_follow),
                                desc = stringResource(
                                    if (dynamicColor) R.string.st_AppSkin_follow_on
                                    else R.string.st_AppSkin_follow_off
                                ),
                                checked = followDynamic,
                                enabled = dynamicColor,
                                onCheckedChange = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.setAppIconFollowDynamic(it)
                                },
                            )
                        } else {
                            SkinMiniNote(
                                title = stringResource(R.string.st_AppSkin_follow),
                                desc = stringResource(R.string.st_AppSkin_follow_note, current.name),
                            )
                        }
                        SkinMiniSwitch(
                            title = stringResource(R.string.st_AppSkin_gradient),
                            desc = stringResource(R.string.st_AppSkin_gradient_desc),
                            checked = iconGradient,
                            onCheckedChange = {
                                vibrationManager?.vibrateClick()
                                viewModel.setAppIconGradientEnabled(it)
                            },
                        )
                        if (bgOv != null || fgOv != null || strokeOv != null) {
                            SkinMiniAction(
                                title = stringResource(R.string.st_AppSkin_custom_icon),
                                desc = stringResource(R.string.st_AppSkin_custom_icon_desc),
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.clearIconOverrides()
                                },
                            )
                        }
                    }
                }

                AppSkinSectionHeader(stringResource(R.string.st_AppSkin_header_theme))

                // Theme & colors (migrated from Settings Visuals & theme)
                AppSkinThemeSection(viewModel = viewModel)

                AppSkinSectionHeader(stringResource(R.string.st_AppSkin_header_skins))

                // Skins — the 3 most recently used looks (bundled presets until you apply some).
                SettingsItem(
                    title = stringResource(R.string.st_AppSkin_skins_title),
                    subtitle = stringResource(
                        if (recentIds.isEmpty()) R.string.st_AppSkin_skins_starter
                        else R.string.st_AppSkin_skins_recent,
                        skins.size,
                    ) + if (syncing) stringResource(R.string.st_AppSkin_syncing) else "",
                    icon = Icons.Rounded.Style,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                        // My shared looks with review status.
                        if (mySubmissions.isNotEmpty()) {
                            mySubmissions.forEach { sub ->
                                val st = submissionStatuses[sub.id]
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sub.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                stringResource(
                                                    R.string.st_AppSkin_made_by,
                                                    userName.ifBlank {
                                                        stringResource(R.string.st_AppSkin_made_by_you)
                                                    },
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            )
                                        }
                                        SubmissionStatusChip(st)
                                    }
                                }
                            }
                        }
                        recentSkins.forEach { skin ->
                            SkinRowCard(
                                skin = skin,
                                selected = skin.id == activeSkinId,
                                isDark = isDark,
                                dynamicColor = dynamicColor,
                                schemePrimary = scheme.primary,
                                schemeSecondary = scheme.secondary,
                                iconGradient = iconGradient,
                                onApply = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.applySkin(skin)
                                },
                            )
                        }
                        ToolzExpressiveButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onOpenSkins()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(stringResource(R.string.st_AppSkin_open_catalog), fontWeight = FontWeight.Black)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolzExpressiveButton(
                                onClick = { scope.launch { viewModel.refreshSkins(true) } },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text(stringResource(R.string.st_FocusFlowScreen_7c4d), fontWeight = FontWeight.Black)
                            }
                            ToolzExpressiveButton(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    showShare = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text(stringResource(R.string.st_AppSkin_share), fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            stringResource(R.string.st_AppSkin_skins_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showShare) {
        var name by remember { mutableStateOf("") }
        var author by remember(userName) { mutableStateOf(userName) }
        var submitting by remember { mutableStateOf(false) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!submitting) showShare = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_AppSkin_share_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    // Live preview of exactly what gets packaged.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ToolzMarkThemed(
                            presetId = presetId,
                            isDark = isDark,
                            dynamicColor = dynamicColor,
                            scheme = scheme,
                            shape = shape,
                            adaptToTheme = adaptTheme,
                            followDynamic = followDynamic,
                            bgOverride = bgOv,
                            fgOverride = fgOv,
                            strokeOverride = strokeOv,
                            iconGradient = iconGradient,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${themeModeLabel(themeMode)} · ${
                                    if (dynamicColor) stringResource(R.string.st_AppSkin_mode_dynamic)
                                    else stringResource(R.string.st_AppSkin_mode_custom_colors)
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${current.name} · ${iconShapeLabel(shape)}" +
                                    if (iconGradient) " · ${stringResource(R.string.st_AppSkin_gradient_word)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.st_AppSkin_share_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        placeholder = { Text(stringResource(R.string.st_AppSkin_share_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it.take(32) },
                        placeholder = { Text(stringResource(R.string.st_AppSkin_share_author_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showShare = false }, enabled = !submitting) { Text(stringResource(R.string.st_Common_Cancel)) }
                        androidx.compose.material3.Button(
                            onClick = {
                                if (name.trim().length >= 3 && !submitting) {
                                    submitting = true
                                    viewModel.submitCurrentAsSkin(name.trim(), author.trim()) { r ->
                                        shareResult = r
                                        submitting = false
                                        showShare = false
                                    }
                                }
                            },
                            enabled = name.trim().length >= 3 && !submitting,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (submitting) R.string.st_AppSkin_sharing
                                    else R.string.st_AppSkin_submit
                                ),
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }

    shareResult?.let { r ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { shareResult = null }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ok = r.isSuccess
                    val dupe = r.exceptionOrNull() as? com.frerox.toolz.data.skin.DuplicateSkinException
                    Text(
                        stringResource(
                            if (ok) R.string.st_AppSkin_shared_ok
                            else if (dupe != null) R.string.st_AppSkin_shared_dup
                            else R.string.st_AppSkin_shared_fail
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (ok) stringResource(R.string.st_AppSkin_shared_pending)
                        else if (dupe != null) stringResource(R.string.st_AppSkin_shared_dup_msg, dupe.existingName)
                        else (r.exceptionOrNull()?.message ?: stringResource(R.string.st_AppSkin_shared_err)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = { shareResult = null },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.st_AiAssistantScreen_u7v9), fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

/** Small-caps section label used to group the App Skin screen. */
@Composable
private fun AppSkinSectionHeader(text: String) {
    @Suppress("DEPRECATION")
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Compact switch row for inside the merged Icon colors card. */
@Composable
private fun SkinMiniSwitch(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Read-only explainer for toggles that don't apply to the current icon. */
@Composable
private fun SkinMiniNote(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
    }
}

/** Compact action row for inside the merged Icon colors card. */
@Composable
private fun SkinMiniAction(title: String, desc: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun Color.appSkinLuminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/**
 * Explains why the Home-screen tile can ignore the chosen preset: a launcher
 * icon pack resolves the tile before the manifest alias is read. Long-pressing
 * an icon pins the real art as a shortcut, which packs do not re-theme.
 */
@Composable
private fun IconPackInfoCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.st_AppSkin_iconpack_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.st_AppSkin_iconpack_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * Developer-mode photo icon picker. One preview, one primary action, and the
 * secondary actions only when they make sense — unlike the old stacked row,
 * there is nothing to scan past.
 *
 * The photo is an in-app mark only. Android requires launcher art to be
 * declared in the manifest, so the Home-screen tile cannot follow it; the pin
 * action offers the shortcut escape hatch instead.
 */
@Composable
private fun PhotoIconCard(
    hasPhoto: Boolean,
    isActive: Boolean,
    shape: String,
    photoVersion: Int,
    busy: Boolean,
    failed: Boolean,
    onPick: () -> Unit,
    onUseSaved: () -> Unit,
    onRemove: () -> Unit,
    onPin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val vibrationManager = LocalVibrationManager.current
    val status = when {
        isActive -> stringResource(R.string.st_AppSkin_custom_status_active)
        hasPhoto -> stringResource(R.string.st_AppSkin_custom_status_saved)
        else -> stringResource(R.string.st_AppSkin_custom_subtitle)
    }
    val primaryLabel = when {
        busy -> stringResource(R.string.st_AppSkin_custom_applying)
        isActive -> stringResource(R.string.st_AppSkin_custom_replace)
        hasPhoto -> stringResource(R.string.st_AppSkin_custom_use_saved)
        else -> stringResource(R.string.st_AppSkin_custom_pick)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isActive) scheme.primary.copy(alpha = 0.14f)
                    else scheme.surface.copy(alpha = 0.55f),
                    border = if (isActive) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)) else null,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        if (hasPhoto) {
                            key(photoVersion) {
                                ToolzMark(
                                    presetId = IconCatalog.CUSTOM_ID,
                                    shape = shape,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = null,
                                tint = scheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.st_AppSkin_custom_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        ExperimentalBadge()
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolzExpressiveButton(
                    onClick = if (hasPhoto && !isActive) onUseSaved else onPick,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(
                        if (isActive) Icons.Rounded.Refresh else Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(primaryLabel, fontWeight = FontWeight.Black)
                }
                if (hasPhoto) {
                    // Icon-only keeps the primary label from squeezing on
                    // narrow screens; TalkBack still reads "Remove photo".
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onRemove()
                        },
                        enabled = !busy,
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.st_AppSkin_custom_remove),
                            tint = scheme.error,
                        )
                    }
                }
            }

            if (isActive) {
                TextButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onPin()
                    },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Icon(
                        Icons.Rounded.AddToHomeScreen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_AppSkin_custom_pin), fontWeight = FontWeight.Bold)
                }
            }

            if (failed) {
                Text(
                    stringResource(R.string.st_AppSkin_custom_error),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.error,
                )
            }
        }
    }
}

/** Small tonal pill that marks a developer-mode experiment. */
@Composable
private fun ExperimentalBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            stringResource(R.string.st_AppSkin_custom_experimental).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
