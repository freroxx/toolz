/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
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
    val activeSkinId by viewModel.activeSkinId.collectAsState(initial = null)
    val skins by viewModel.skins.collectAsState(initial = emptyList())
    val syncing by viewModel.skinsSyncing.collectAsState(initial = false)
    val userName by viewModel.userName.collectAsState(initial = "")
    val mySubmissions by viewModel.mySubmissions.collectAsState(initial = emptyList())
    val submissionStatuses by viewModel.submissionStatuses.collectAsState(initial = emptyMap())

    var showShare by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<Result<String>?>(null) }

    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = MaterialTheme.colorScheme
    val current = IconCatalog.find(presetId)

    LaunchedEffect(Unit) { viewModel.refreshSkins(false) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "App Skin",
                subtitle = "Icons, colors & community skins",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                            modifier = Modifier.size(112.dp),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(
                                "${shape.lowercase()} · ${current.tag.lowercase()} · ${activeSkinId ?: "custom look"}",
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
                                Text("Icon Catalog", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // Icon presets strip
                SettingsItem(
                    title = "Icon style",
                    subtitle = "${IconCatalog.BUILT_IN.size} presets — tap to apply instantly",
                    icon = Icons.Rounded.Palette,
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
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        viewModel.setAppIconPresetId(spec.id)
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    ),
                                    modifier = Modifier.size(68.dp).bouncyClick {},
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

                // Appearance behavior
                SettingsToggleItem(
                    title = "Adapt to theme",
                    subtitle = "Icon shades shift for dark / light mode",
                    icon = Icons.Rounded.Palette,
                    checked = adaptTheme,
                    onCheckedChange = { viewModel.setAppIconAdaptTheme(it) },
                )
                SettingsToggleItem(
                    title = "Follow dynamic colors",
                    subtitle = if (dynamicColor) "Accents follow your wallpaper (Material You)"
                    else "Needs Dynamic color (Visuals & theme) — otherwise the icon uses theme + your primary / secondary",
                    icon = Icons.Rounded.Palette,
                    checked = followDynamic,
                    onCheckedChange = { viewModel.setAppIconFollowDynamic(it) },
                    enabled = dynamicColor,
                )
                SettingsItem(
                    title = "Icon shape",
                    subtitle = "Container for the mark",
                    icon = Icons.Rounded.GridView,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HEXAGON", "CIRCLE", "PILL").forEach { s ->
                            val sel = shape == s
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
                                    Text(s, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                SettingsToggleItem(
                    title = "Icon gradient",
                    subtitle = "Depth shading on supporting presets",
                    icon = Icons.Rounded.Palette,
                    checked = iconGradient,
                    onCheckedChange = { viewModel.setAppIconGradientEnabled(it) },
                )
                if (bgOv != null || fgOv != null || strokeOv != null) {
                    SettingsItem(
                        title = "Custom icon colors",
                        subtitle = "Tap to return to the preset",
                        icon = Icons.Rounded.RestartAlt,
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.clearIconOverrides()
                        },
                    )
                }

                // Skins
                SettingsItem(
                    title = "Toolz Skins",
                    subtitle = "${skins.size} looks ${if (syncing) "· syncing…" else ""}",
                    icon = Icons.Rounded.Palette,
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
                                                "made by ${userName.ifBlank { "you" }}",
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
                        skins.take(4).forEach { skin ->
                            SkinRowCard(
                                skin = skin,
                                selected = skin.id == activeSkinId,
                                isDark = isDark,
                                dynamicColor = dynamicColor,
                                schemePrimary = scheme.primary,
                                schemeSecondary = scheme.secondary,
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
                            Text("Open Skins Catalog", fontWeight = FontWeight.Black)
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
                                Text("Refresh", fontWeight = FontWeight.Black)
                            }
                            ToolzExpressiveButton(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    showShare = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text("Share my look", fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            "Skins apply theme + colors + icon together. Shared looks go to review, then appear for everyone.",
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
                    Text("Share your look", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "Packages your theme + colors + icon as a community skin for review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        placeholder = { Text("Look name — e.g. Midnight Grape") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it.take(32) },
                        placeholder = { Text("Your name — shown as made by …") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showShare = false }, enabled = !submitting) { Text("Cancel") }
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
                        ) { Text(if (submitting) "Sharing…" else "Submit", fontWeight = FontWeight.Black) }
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
                    Text(
                        if (ok) "Look shared!" else "Share failed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (ok) "It's pending review — you'll see Pending review, then Live or Not approved, right here in My shared looks."
                        else (r.exceptionOrNull()?.message ?: "Something went wrong. Check your connection and try again."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = { shareResult = null },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Got it", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}
